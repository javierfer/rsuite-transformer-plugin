package com.rsicms.rsuite.transformer.utils;

import static com.reallysi.rsuite.api.ObjectType.*;
import static com.reallysi.rsuite.api.RSuiteException.*;
import static org.apache.commons.lang.StringUtils.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;

import org.apache.commons.io.*;
import org.apache.commons.lang.*;

import com.reallysi.rsuite.api.*;
import com.reallysi.rsuite.api.control.*;
import com.reallysi.rsuite.api.extensions.*;
import com.reallysi.rsuite.api.tools.*;
import com.reallysi.rsuite.service.*;
import com.rsicms.rsuite.helpers.utils.*;

public class MOUtils {
	
private MOUtils() {}
	
	public static boolean checkout(
			ExecutionContext context,
			User user,
			String id) 
	throws RSuiteException {
		ManagedObjectService moService = context.getManagedObjectService();
		if (!moService.isCheckedOut(user, id)) {
			moService.checkOut(user, id);
			return true;
		} else {
			if (moService.isCheckedOutButNotByUser(user, id)) {
				throw new RSuiteException(
					RSuiteException.ERROR_INTERNAL_ERROR,
					"Another user or process has the check out for this MO id: " + id);
			}
			return false; 
		}	
	}
	
	public static ObjectSource getObjectSource(
			ExecutionContext context,
			User user, String filename,
			InputStream content,
			String encoding) 
	throws IOException {
		byte[] data = IOUtils.toByteArray(content);
		if (context.getRSuiteServerConfiguration().isTreatAsXmlFileExtension(
				FilenameUtils.getExtension(filename))) {
			return new XmlObjectSource(data, encoding);
		} else {
			/* ManageableObjectService#update() NPE when using byte arrays. Only files work. */
			File tmpDir = context.getRSuiteServerConfiguration().getTmpDir();
			File outputDir = new File(tmpDir, user.getUserId());
			outputDir.mkdirs();
			File tmpFile = new File(outputDir, filename);
			FileUtils.writeByteArrayToFile(tmpFile,  data);

			return new NonXmlObjectSource(tmpFile);
		}
	}
	
	public static void updateAndCheckIn(ExecutionContext context, User user, ManagedObjectService moService, ObjectSource objectSource,
			ManagedObject existingMo, String versionNote) throws RSuiteException, ValidationException {
		ObjectUpdateOptions options = new ObjectUpdateOptions();

		String fileName = getMoFileNameAlias(context, user, existingMo);
		options.setExternalFileName(fileName);

		if (!objectSource.isXml())
			options.setDisplayName(fileName);

		String contentType = context.getConfigurationService().getMimeMappingCatalog()
				.getMimeTypeByExtension(FilenameUtils.getExtension(fileName));
		options.setContentType(contentType);

		moService.update(
				user, 
				existingMo.getId(), 
				objectSource,
				options);
			
		// Check in the MO
		ObjectCheckInOptions checkInOptions = new ObjectCheckInOptions();
		checkInOptions.setVersionType(VersionType.MINOR);
		checkInOptions.setVersionNote(versionNote);
		moService.checkIn(user, existingMo.getId(), checkInOptions);
	}
	
	public static ManagedObject getMoByFileNameAliasFromContainer(ExecutionContext context, User user, String fileName,
			ContentAssemblyNodeContainer caContainer) throws RSuiteException {
		ManagedObjectService moService = context.getManagedObjectService();

		AliasHelper aliasHelper = moService.getAliasHelper();

		for (ContentAssemblyItem item : caContainer.getChildrenObjects()) {
			ObjectType objectType = item.getObjectType();
			if (objectType == MANAGED_OBJECT_REF) {
				ManagedObjectReference moRef = (ManagedObjectReference) item;
				ManagedObject candidateMo = moService.getManagedObject(user, moRef.getTargetId());
				String filenameAliasRealMo = aliasHelper.getFilename(user, candidateMo);
				if (StringUtils.equals(fileName, filenameAliasRealMo)) {
					return candidateMo;
				}
			}
		}
		return null;
	}
	
	public static String getMoFileNameAlias(ExecutionContext context, User user, ManagedObject mo) throws RSuiteException {
		ManagedObjectService moService = context.getManagedObjectService();
		AliasHelper aliasHelper = moService.getAliasHelper();
		return aliasHelper.getFilename(user, mo);
	}

	public static String getMoBaseNameAlias(ExecutionContext context, User user, ManagedObject mo) throws RSuiteException {
		Alias[] aliases = mo.getAliases("basename");
		String basename = StringUtils.EMPTY;

		if (aliases != null && aliases.length > 0) {
			basename = aliases[0].getText();
		}
		return basename;
	}

	public static void insertAndAttach(ExecutionContext context, User user, ManagedObject caMo, String fileName,
			ObjectSource objectSource) throws RSuiteException {
		ManagedObjectService moService = context.getManagedObjectService();
		ContentAssemblyService caService = context.getContentAssemblyService();

		ObjectInsertOptions options = new ObjectInsertOptions(fileName, new String[0], new String[0],
				objectSource.isXml(), objectSource.isXml());

		options.setFileName(fileName);

		if (!objectSource.isXml())
			options.setDisplayName(fileName);

		String contentType = context.getConfigurationService().getMimeMappingCatalog()
				.getMimeTypeByExtension(FilenameUtils.getExtension(fileName));
		options.setContentType(contentType);

		ManagedObject topic = moService.load(user, objectSource, options);

		caService.attach(user, caMo.getId(), topic, new ObjectAttachOptions());
	}

	public static void applyTransformAndInsert(
			ExecutionContext context,
			Session session,
			ManagedObject mo,
			ManagedObject caMo,
			String xslUri,
			String fileBaseName,
			String fileExtension,
			String protocol,
			Map<String, String> xslParams) throws RSuiteException {
		User user = session.getUser();
		ManagedObjectService moService = context.getManagedObjectService();
		boolean createdCheckOut = false;
		InputStream transformResult = null;
		try {
			
			// Make sure the user has the check out.
			createdCheckOut = checkout(context, user, mo.getId());
			
			// Perform transform
			transformResult = DomUtils.transform(
				context, 
				session, 
				mo.getElement().getOwnerDocument(), 
				context.getXmlApiManager().getTransformer(new URI(xslUri)),
				protocol,
				xslParams);

			if (isBlank(fileBaseName)) {
				fileBaseName = getMoBaseNameAlias(context, user, mo);
			}

			if (isBlank(fileExtension)) {
				fileExtension = FilenameUtils.getExtension(getMoFileNameAlias(context, user, mo));
			}

			String fileName = fileBaseName + "." + fileExtension;

			// Update the MO
			ObjectSource objectSource = getObjectSource(
				context,
				user,
				fileName, 
				transformResult, 
				StandardCharsets.UTF_8.name());

			insertAndAttach(context, user, caMo, fileName, objectSource);

			if (createdCheckOut && moService.isCheckedOutAuthor(user, mo.getId())) {
				moService.undoCheckout(user, mo.getId());
			}
		} catch(Exception e) {
			throw new RSuiteException(ERROR_OBJECT_INSERT_ERR,
					"Transformation and Insert error: " + e.getMessage(), e);

		} finally {
			IOUtils.closeQuietly(transformResult);
		}
	}

	public static void applyTransformAndUpdate(
			ExecutionContext context,
			Session session,
			ManagedObject mo,
			ManagedObject caMo,
			String xslUri,
			String fileBaseName,
			String fileExtension,
			String protocol,
			Map<String, String> xslParams) throws RSuiteException {
		User user = session.getUser();
		ManagedObjectService moService = context.getManagedObjectService();
		boolean createdCheckOut = false;
		InputStream transformResult = null;
		try {
			
			// Make sure the user has the check out.
			createdCheckOut = checkout(context, user, mo.getId());
			
			// Perform transform
			transformResult = DomUtils.transform(
				context, 
				session, 
				mo.getElement().getOwnerDocument(), 
				context.getXmlApiManager().getTransformer(new URI(xslUri)),
				protocol,
				xslParams);

			if (isBlank(fileBaseName)) {
				fileBaseName = getMoBaseNameAlias(context, user, mo);
			}
			
			if (isBlank(fileExtension)) {
				fileExtension = FilenameUtils.getExtension(getMoFileNameAlias(context, user, mo));
			}

			String fileName = fileBaseName + "." + fileExtension;

			// Update the MO
			ObjectSource objectSource = getObjectSource(
				context,
				user,
				fileName, 
				transformResult, 
				StandardCharsets.UTF_8.name());

			ContentAssemblyNodeContainer caContainer = 
					RSuiteUtils.getContentAssemblyNodeContainer(context, user, caMo.getId());
			ManagedObject existingMo = 
					getMoByFileNameAliasFromContainer(context, user, fileName, caContainer);
			
			if (existingMo != null) {
				boolean existinMoCheckOut = checkout(context, user, existingMo.getId());
				// update
				updateAndCheckIn(context, user, moService, objectSource, existingMo, "MO transformed.");

				if (existinMoCheckOut && moService.isCheckedOutAuthor(user, existingMo.getId())) {
					moService.undoCheckout(user, existingMo.getId());
				}
			} else {
				//insert
				insertAndAttach(context, user, caMo, fileName, objectSource);
			}

			if (createdCheckOut && moService.isCheckedOutAuthor(user, mo.getId())) {
				moService.undoCheckout(user, mo.getId());
			}
		} catch(Exception e) {
			throw new RSuiteException(ERROR_OBJECT_UPDATE_ERR,
					"Transformation and Update error: " + e.getMessage(), e);
			
		} finally {
			IOUtils.closeQuietly(transformResult);
		}
	}

	public static void applyTransformAndOverwrite(
			ExecutionContext context,
			Session session,
			ManagedObject mo,
			String xslUri,
			String protocol,
			Map<String, String> xslParams) throws RSuiteException {
		User user = session.getUser();
		ManagedObjectService moService = context.getManagedObjectService();
		boolean createdCheckOut = false;
		InputStream transformResult = null;
		try {
			// Make sure the user has the check out.
			createdCheckOut = checkout(context, user, mo.getId());
			
			// Perform transform
			transformResult = DomUtils.transform(
				context, 
				session, 
				mo.getElement().getOwnerDocument(), 
				context.getXmlApiManager().getTransformer(new URI(xslUri)),
				protocol,
				xslParams);
			
			// Update the MO
			ObjectSource objectSource = getObjectSource(
				context, 
				user,
				"file.xml", // Only the file extension matters here. 
				transformResult, 
				StandardCharsets.UTF_8.name());
			
			updateAndCheckIn(context, user, moService, objectSource, mo, "MO transformed.");

			if (createdCheckOut && moService.isCheckedOutAuthor(user, mo.getId())) {
				moService.undoCheckout(user, mo.getId());
			}
		} catch(Exception e) {
			throw new RSuiteException(ERROR_OBJECT_UPDATE_ERR,
					"Transformation and Update error: " + e.getMessage(), e);
		} finally {
			IOUtils.closeQuietly(transformResult);
		}
	}

}

