package com.rsicms.rsuite.transformer.service;

import static com.rsicms.rsuite.TransformerConstants.*;
import static com.rsicms.rsuite.transformer.utils.MOUtils.*;
import static org.apache.commons.lang.StringUtils.*;

import java.util.*;

import com.reallysi.rsuite.api.*;
import com.reallysi.rsuite.api.remoteapi.*;

public class TransformerService {

	private RemoteApiExecutionContext context;

	public TransformerService(RemoteApiExecutionContext context) {
		this.context = context;
	}

	public void transformAndAttach(
			ManagedObject mo, 
			ManagedObject caMo, 
			Map<String, String> parameters) throws RSuiteException {
		
		Map<String, String> xsltParams = resolveXsltParameters(parameters);

		String operation = parameters.get(OPERATION);
		
		if (OPERATION_INSERT.equalsIgnoreCase(operation)) {
			applyTransformAndInsert(
					context, 
					context.getSession(), 
					mo, 
					caMo, 
					parameters.get(TRANSFORM),
					parameters.get(OUTPUT_FILENAME_ALIAS),
					parameters.get(OUTPUT_FILE_EXTENSION_ALIAS),
					parameters.get(OUTPUT_FILE_PREFIX_ALIAS),
					parameters.get(HOST_PROTOCOL),
					xsltParams);
		} else if (OPERATION_UPDATE.equalsIgnoreCase(operation)) {
			applyTransformAndUpdate(
					context, 
					context.getSession(), 
					mo, 
					caMo, 
					parameters.get(TRANSFORM),
					parameters.get(OUTPUT_FILENAME_ALIAS),
					parameters.get(OUTPUT_FILE_EXTENSION_ALIAS),
					parameters.get(OUTPUT_FILE_PREFIX_ALIAS),
					parameters.get(HOST_PROTOCOL),
					xsltParams);
			
		} else if (OPERATION_UPDATE_OVERWRITE.equalsIgnoreCase(operation)) {
			applyTransformAndOverwrite(
					context, 
					context.getSession(), 
					mo, 
					parameters.get(TRANSFORM),
					parameters.get(HOST_PROTOCOL),
					xsltParams);
		}
		else {
			throw new RSuiteException(String.format("%s: '%s' not supported.", OPERATION, operation));
		}
		
	}

	public Map<String, String> resolveXsltParameters(Map<String, String> parameters) {
		Map<String, String> xsltParams = new HashMap<String, String>();
		
		for (Map.Entry<String, String> entry : parameters.entrySet()) {
			if (startsWith(entry.getKey(), XSLT_PARAM_PREFIX)) {
				String realKey = removeStart(entry.getKey(), XSLT_PARAM_PREFIX);
				xsltParams.put(realKey, entry.getValue());
			}
		}
		return xsltParams;
	}
}
