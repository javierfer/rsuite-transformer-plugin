package com.rsicms.rsuite.transformer.utils;

import static com.reallysi.rsuite.api.ObjectType.*;
import static com.reallysi.rsuite.api.RSuiteException.*;
import static org.apache.commons.lang.StringUtils.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.reallysi.rsuite.api.*;
import com.reallysi.rsuite.api.control.NonXmlObjectSource;
import com.reallysi.rsuite.api.control.ObjectAttachOptions;
import com.reallysi.rsuite.api.control.ObjectCheckInOptions;
import com.reallysi.rsuite.api.control.ObjectInsertOptions;
import com.reallysi.rsuite.api.control.ObjectSource;
import com.reallysi.rsuite.api.control.ObjectUpdateOptions;
import com.reallysi.rsuite.api.control.XmlObjectSource;
import com.reallysi.rsuite.api.extensions.ExecutionContext;
import com.reallysi.rsuite.api.tools.AliasHelper;
import com.reallysi.rsuite.service.ContentAssemblyService;
import com.reallysi.rsuite.service.ManagedObjectService;
import com.rsicms.rsuite.helpers.utils.RSuiteUtils;

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
			String filename,
			InputStream content,
			String encoding) 
	throws IOException {
		byte[] data = IOUtils.toByteArray(content);
		if (context.getRSuiteServerConfiguration().isTreatAsXmlFileExtension(
				FilenameUtils.getExtension(filename))) {
			return new XmlObjectSource(data, encoding);
		} else {
			return new NonXmlObjectSource(data);
		}
	}
	
	public static void updateAndCheckIn(User user, ManagedObjectService moService, ObjectSource objectSource,
			ManagedObject existingMo, String versionNote) throws RSuiteException, ValidationException {
		moService.update(
				user, 
				existingMo.getId(), 
				objectSource,
				new ObjectUpdateOptions());
			
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
		
		ObjectInsertOptions options = 
				new ObjectInsertOptions(fileName, new String[0], new String[0], true);
		
		ManagedObject topic = moService.load(user, objectSource, options);
		
		caService.attach(user, caMo.getId(), topic, new ObjectAttachOptions());
	}

	public static void applyTransformAndInsert(
			ExecutionContext context,
			Session session,
			ManagedObject mo,
			ManagedObject caMo,
			String xslUri,
			String fileName,
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
			
			// Update the MO
			ObjectSource objectSource = getObjectSource(
				context, 
				"file.xml", // Only the file extension matters here. 
				transformResult, 
				StandardCharsets.UTF_8.name());
			
			if (isBlank(fileName)) {
				fileName = getMoBaseNameAlias(context, user, mo);
			}

			if (isBlank(fileExtension)) {
				fileExtension = FilenameUtils.getExtension(getMoFileNameAlias(context, user, mo));
			}
			
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
			String fileName,
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
			
			// Update the MO
			ObjectSource objectSource = getObjectSource(
				context, 
				"file.xml", // Only the file extension matters here. 
				transformResult, 
				StandardCharsets.UTF_8.name());

			if (isBlank(fileName)) {
				fileName = getMoBaseNameAlias(context, user, mo);
			}

			if (isBlank(fileExtension)) {
				fileExtension = FilenameUtils.getExtension(getMoFileNameAlias(context, user, mo));
			}

			ContentAssemblyNodeContainer caContainer = 
					RSuiteUtils.getContentAssemblyNodeContainer(context, user, caMo.getId());
			ManagedObject existingMo = 
					getMoByFileNameAliasFromContainer(context, user, fileName, caContainer);
			
			if (existingMo != null) {
				boolean existinMoCheckOut = checkout(context, user, existingMo.getId());
				// update
				updateAndCheckIn(user, moService, objectSource, existingMo, "MO transformed.");

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
				"file.xml", // Only the file extension matters here. 
				transformResult, 
				StandardCharsets.UTF_8.name());
			
			updateAndCheckIn(user, moService, objectSource, mo, "MO transformed.");

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

