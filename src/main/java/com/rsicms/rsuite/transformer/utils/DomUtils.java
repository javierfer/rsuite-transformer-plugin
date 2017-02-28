package com.rsicms.rsuite.transformer.utils;

import static org.apache.commons.lang.StringUtils.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.Session;
import com.reallysi.rsuite.api.extensions.ExecutionContext;
import com.reallysi.rsuite.api.system.RSuiteServerConfiguration;

public class DomUtils {
	
	protected static Log log = LogFactory.getLog(DomUtils.class);

	private DomUtils() {}
	
	public static InputStream transform(
			ExecutionContext context,
			Session session,
			Document inputDoc,
			Transformer transformer,
			String protocol,
			Map<String, String> xslParams) 
	throws RSuiteException, URISyntaxException, TransformerException, SAXException, IOException {
		
		ByteArrayOutputStream outputStream = null;

		try {
			outputStream = new ByteArrayOutputStream();
			StreamResult streamResult = new StreamResult(outputStream);
			transformer.clearParameters();
			
			// Pass on parameters
			if (xslParams == null) {
				xslParams = new HashMap<String, String>();
			}
			
			if (isEmpty(protocol)) {
				protocol = "http";
			}
			
			RSuiteServerConfiguration serverConfig = context.getRSuiteServerConfiguration();
			String url = protocol + "://" + serverConfig.getHostName() + ":" + serverConfig.getPort();
			
			log.info("Transform URL: " + url);
			
			transformer.setParameter("rsuite.serverurl", url);
			transformer.setParameter("rsuite.sessionkey", session.getKey());
			transformer.setParameter("rsuite.username", session.getUser().getUserId());
			
			for (Map.Entry<String, String> entry : xslParams.entrySet()) {
				transformer.setParameter(entry.getKey(), entry.getValue());
			}
			
			DOMSource domSource = new DOMSource(inputDoc);
			domSource.setSystemId(inputDoc.getDocumentURI());

			transformer.transform(
				domSource, 
				streamResult);
			return new ByteArrayInputStream(outputStream.toByteArray());
		} finally {
			IOUtils.closeQuietly(outputStream);
		}
	}
}
