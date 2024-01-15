package network.oxalis.as4.outbound;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.soap.SOAPFaultException;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;

import lombok.extern.slf4j.Slf4j;
import network.oxalis.api.outbound.MessageSender;
import network.oxalis.api.outbound.TransmissionRequest;
import network.oxalis.api.outbound.TransmissionResponse;
import network.oxalis.as4.lang.OxalisAs4TransmissionException;
import network.oxalis.as4.util.AS4ErrorCode;
import network.oxalis.commons.guice.GuiceModuleLoader;
import network.oxalis.vefa.peppol.common.model.DocumentTypeIdentifier;
import network.oxalis.vefa.peppol.common.model.Endpoint;
import network.oxalis.vefa.peppol.common.model.Header;
import network.oxalis.vefa.peppol.common.model.ParticipantIdentifier;
import network.oxalis.vefa.peppol.common.model.ProcessIdentifier;
import network.oxalis.vefa.peppol.common.model.TransportProfile;

@Slf4j
public class OxalisUnsignedSignalErrorResponseTest {

	@Test
	public void testSendMessageUnsignedResponse() throws Exception {
		Server server = startServer();
		try {
			Injector injector = getInjector();
			MessageSender messageSender = injector.getInstance(Key.get(MessageSender.class, Names.named("oxalis-as4")));
			TransmissionResponse transResponse = messageSender.send(buildTransmissionRequest(injector));

			log.info("Unexpected successful result: ");
			log.info(transResponse.toString());
			fail("Sending is expected to fail");
		} catch (Exception e) {
			log.error("Result exception:");
			e.printStackTrace();
			assertEquals(e.getClass().getName(), OxalisAs4TransmissionException.class.getName());
			assertEquals(e.getCause().getClass().getName(), SOAPFaultException.class.getName());
			assertEquals(e.getCause().getCause().getClass().getName(), OxalisUnsignedSignalErrorResponseException.class.getName());

			OxalisUnsignedSignalErrorResponseException u = (OxalisUnsignedSignalErrorResponseException) e.getCause().getCause();

			String errorDescription = "Invoked AS4 message processor SPI com.helger.phase4.peppol.servlet.Phase4PeppolServletMessageProcessorSPI@40b6cf9e on 'b237d76c-6a22-45af-8b3c-f5435222e57b@vmi1175806' returned a failure: TBErrorResponse{errorCode='-4115', errorDescription='Unable to correlate request with Document's Identifier [POP000XXX-2-20230703T135007]', trace='7b2c0688-02d1-4e2c-99b3-6a2f2008a1e8'}";

			assertEquals(u.getErrorDescription(), errorDescription);
			assertEquals(u.getSeverity(), AS4ErrorCode.Severity.FAILURE);
			assertEquals(u.getErrorCode(), AS4ErrorCode.EBMS_0004);
		} finally {
			server.stop();
		}
	}

	private TransmissionRequest buildTransmissionRequest(final Injector injector) {
		return new TransmissionRequest() {
			@Override
			public Endpoint getEndpoint() {
				return Endpoint.of(TransportProfile.AS4, URI.create("http://localhost:8080/as4"),
						injector.getInstance(X509Certificate.class));
			}

			@Override
			public Header getHeader() {
				return Header.newInstance()
						.sender(ParticipantIdentifier.of("0007:5567125082"))
						.receiver(ParticipantIdentifier.of("0007:4455454480"))
						.documentType(DocumentTypeIdentifier.of("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:www.cenbii.eu:transaction:biicoretrdm010:ver1.0:#urn:www.peppol.eu:bis:peppol4a:ver1.0::2.0"))
						.process(ProcessIdentifier.of("urn:www.cenbii.eu:profile:bii04:ver1.0"));
			}

			@Override
			public InputStream getPayload() {
				return new ByteArrayInputStream("<test/>".getBytes());
			}

		};
	}

	private Server startServer() throws Exception {
		Server server = new Server();
		ServerConnector connector = new ServerConnector(server);
		connector.setPort(8080);
		server.setConnectors(new Connector[] { connector });
		ServletHandler servletHandler = new ServletHandler();
		servletHandler.addServletWithMapping(UnsignedResponseServlet.class, "/as4");
		server.setHandler(servletHandler);
		server.start();
		return server;
	}

	private Injector getInjector() {
		return Guice.createInjector(new GuiceModuleLoader());
	}

	public static class UnsignedResponseServlet extends HttpServlet {

		private static final long serialVersionUID = -183035827295313986L;

		protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			doGet(request, response);
		}

		protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			String timestamp = null;
			String messageId = null;
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (InputStream is = request.getInputStream()) {
					IOUtils.copy(is, baos);
				}
				String soapXML = new String(baos.toByteArray(), StandardCharsets.UTF_8);

				log.debug("Received SOAP request:");
				log.debug(soapXML);

				timestamp = extractPart(soapXML, "Timestamp");
				messageId = extractPart(soapXML, "MessageId");

				log.info("Extracted from incoming SOAP Envlope timestamp=" + timestamp + " and refMessageId=" + messageId);

			} catch (Exception e) {
				log.error("Failed to extract Timestamp and MessageId from request", e);
			}
			String result = RESPONSE_TEMPLATE;
			if (timestamp != null && messageId != null) {
				result = result.replace("${RESPONSE_TIMESTAMP}", timestamp).replace("${REF_MESSAGE_ID}", messageId);
			}
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().println(result);
		}

		private String extractPart(String xml, String tag) {
			String startPart = ":" + tag + ">";
			int start = xml.indexOf(startPart);
			if (start >= 0) {
				int end = xml.indexOf("</", start);
				if (end >= 0) {
					return xml.substring(start + startPart.length(), end);
				}
			}
			return null;
		}
	}

	private static String RESPONSE_TEMPLATE = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\r\n"
			+ "<Envelope xmlns=\"http://www.w3.org/2003/05/soap-envelope\">\r\n"
			+ "	<Header>\r\n"
			+ "		<eb:Messaging\r\n"
			+ "			xmlns:eb=\"http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/\" \r\n"
			+ "			xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\" \r\n"
			+ "			wsu:Id=\"phase4-msg-825fb7c7-c46f-4a48-808a-960960a009c4\">\r\n"
			+ "			<eb:SignalMessage>\r\n"
			+ "				<eb:MessageInfo>\r\n"
			+ "					<eb:Timestamp>${RESPONSE_TIMESTAMP}</eb:Timestamp>\r\n"
			+ "					<eb:MessageId>3026e747-172b-4e35-b851-ee4c45d9602c@phase4</eb:MessageId>\r\n"
			+ "					<eb:RefToMessageId>${REF_MESSAGE_ID}</eb:RefToMessageId>\r\n"
			+ "				</eb:MessageInfo>\r\n"
			+ "				<eb:Error category=\"Content\" errorCode=\"EBMS:0004\" refToMessageInError=\"b237d76c-6a22-45af-8b3c-f5435222e57b@vmi1175806\" severity=\"failure\" shortDescription=\"Other\">\r\n"
			+ "					<eb:Description xml:lang=\"en\">Invoked AS4 message processor SPI com.helger.phase4.peppol.servlet.Phase4PeppolServletMessageProcessorSPI@40b6cf9e on 'b237d76c-6a22-45af-8b3c-f5435222e57b@vmi1175806' returned a failure: TBErrorResponse{errorCode='-4115', errorDescription='Unable to correlate request with Document's Identifier [POP000XXX-2-20230703T135007]', trace='7b2c0688-02d1-4e2c-99b3-6a2f2008a1e8'}</eb:Description>\r\n"
			+ "					<eb:ErrorDetail>An undefined error occurred.</eb:ErrorDetail>\r\n"
			+ "				</eb:Error>\r\n"
			+ "			</eb:SignalMessage>\r\n"
			+ "		</eb:Messaging>\r\n"
			+ "	</Header>\r\n"
			+ "	<Body/>\r\n"
			+ "</Envelope>";
}
