package com.rsicms.rsuite.transformer.utils;

import static org.apache.commons.lang.StringUtils.isBlank;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.SAXException;

import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.Session;
import com.reallysi.rsuite.api.extensions.ExecutionContext;
import com.reallysi.rsuite.api.system.RSuiteServerConfiguration;
import com.reallysi.rsuite.api.xml.LoggingSaxonMessageListener;
import com.reallysi.tools.dita.conversion.beans.TransformSupportBean;
import com.rsicms.rsuite.helpers.utils.MoUtils;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.Serializer;

public class DomUtils {

	protected static Log log = LogFactory.getLog(DomUtils.class);

	private DomUtils() {
	}

	public static InputStream transform(ExecutionContext context, Session session, ManagedObject mo, String xslUri,
			String protocol, Map<String, String> xslParams)
			throws RSuiteException, URISyntaxException, TransformerException, SAXException, IOException {

		ByteArrayOutputStream outputStream = null;

		try {

			// Pass on parameters
			if (xslParams == null) {
				xslParams = new HashMap<String, String>();
			}

			RSuiteServerConfiguration serverConfig = context.getRSuiteServerConfiguration();

			if (isBlank(protocol)) {
				protocol = serverConfig.getProtocol();
			}

			String url = protocol + "://" + serverConfig.getHostName() + ":" + serverConfig.getPort();

			log.info("Transform URL: " + url);

			LoggingSaxonMessageListener logger = context.getXmlApiManager().newLoggingSaxonMessageListener(log);
			Map<String, String> params = new HashMap<String, String>();
			params.put("debug", "false");
			params.put("rsuiteSessionKey", session.getKey());
			params.put("rsuiteProtocol", context.getRSuiteServerConfiguration().getProtocol());
			params.put("rsuiteHost", context.getRSuiteServerConfiguration().getHostName());
			params.put("rsuitePort", context.getRSuiteServerConfiguration().getPort());
			params.putAll(xslParams);

			TransformSupportBean tsBean = new TransformSupportBean(context, xslUri);
			Source source = new DOMSource(mo.getElement());

			source.setSystemId(url + "/rsuite/rest/v1/content/" + MoUtils.getIdFromMoTypeId(mo.getMoTypeId()));
			outputStream = new ByteArrayOutputStream();
			Processor processor = new Processor(true);
			Serializer dest = processor.newSerializer(outputStream);

			tsBean.applyTransform(source, dest, params, logger, log);

			return new ByteArrayInputStream(outputStream.toByteArray());
		} finally {
			IOUtils.closeQuietly(outputStream);
		}
	}
}
