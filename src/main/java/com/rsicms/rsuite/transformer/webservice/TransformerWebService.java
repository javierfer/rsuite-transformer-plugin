package com.rsicms.rsuite.transformer.webservice;

import static com.rsicms.rsuite.TransformerConstants.*;
import static java.lang.String.*;
import static org.apache.commons.lang.StringUtils.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.remoteapi.CallArgumentList;
import com.reallysi.rsuite.api.remoteapi.RemoteApiExecutionContext;
import com.reallysi.rsuite.api.remoteapi.RemoteApiResult;
import com.reallysi.rsuite.api.remoteapi.result.MessageDialogResult;
import com.reallysi.rsuite.api.remoteapi.result.MessageType;
import com.rsicms.rsuite.helpers.utils.RSuiteUtils;
import com.rsicms.rsuite.helpers.webservice.RemoteApiHandlerBase;
import com.rsicms.rsuite.transformer.service.TransformerService;

public class TransformerWebService extends RemoteApiHandlerBase {

	protected static Log log = LogFactory.getLog(TransformerWebService.class);

	@Override
	public RemoteApiResult execute(RemoteApiExecutionContext context, CallArgumentList args) throws RSuiteException {

		validateParameters(args);

		User user = context.getSession().getUser();
		ManagedObject ref = args.getFirstManagedObject(user);

		ManagedObject moref = context.getManagedObjectService().getManagedObject(user,
				ref.getDirectReferenceIds().get(0));

		ManagedObject caMo = context.getManagedObjectService().getManagedObject(user, moref.getAncestorMoTypeIds()[0]);

		ManagedObject mo = RSuiteUtils.getRealMo(context, user, ref);

		TransformerService service = new TransformerService(context);

		try {
			service.transformAndAttach(mo, caMo, args.getValuesMap());

		} catch (RSuiteException e) {
			log.error(e);
			return new MessageDialogResult(MessageType.ERROR, "Transformer", e.getMessage());
		}

		return new MessageDialogResult(MessageType.SUCCESS, "Transformer", "MO transformed successfully.");
	}

	private void validateParameters(CallArgumentList args) throws RSuiteException {

		if (isEmpty(args.getFirstString(TRANSFORM)))
			throw new RSuiteException(format("No '%s' parameter found.", TRANSFORM));

		if (isEmpty(args.getFirstString(OPERATION))) {
			log.warn(format("No %s specified. Default is '%s'", OPERATION, OPERATION_UPDATE));
			args.getValuesMap().put(OPERATION, OPERATION_UPDATE);
		}
	}

}
