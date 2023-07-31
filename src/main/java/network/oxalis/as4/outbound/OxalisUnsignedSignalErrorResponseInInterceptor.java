package network.oxalis.as4.outbound;

import java.util.List;
import java.util.Optional;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.ws.policy.PolicyException;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Error;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.SignalMessage;
import org.w3c.dom.Node;

import lombok.extern.slf4j.Slf4j;
import network.oxalis.as4.util.AS4ErrorCode;
import network.oxalis.as4.util.Constants;
import network.oxalis.as4.util.Marshalling;

@Slf4j
public class OxalisUnsignedSignalErrorResponseInInterceptor extends AbstractPhaseInterceptor<Message> {

	public OxalisUnsignedSignalErrorResponseInInterceptor() {
		// Should be invoked just before PolicyVerificationInInterceptor, which is executed on PRE_INVOKE phase
		super(Phase.POST_UNMARSHAL);
	}

	public void handleMessage(Message message) {
		log.debug("OxalisUnsignedSignalErrorResponseInInterceptor.handleMessage");
	}

	public void handleFault(Message message) {
		log.debug("OxalisUnsignedSignalErrorResponseInInterceptor.handleFault");
		Exception exceptionContent = message.getContent(Exception.class);
		
		// Only if Fault reason is PolicyException...
		if (exceptionContent != null && exceptionContent instanceof PolicyException) {
			PolicyException pe = (PolicyException) exceptionContent;
			String messageText = pe.getMessage();
			if (messageText == null) {
				return;
			}
			
			// With exactly these texts inside...
			if (messageText.indexOf("Soap Body is not SIGNED") < 0 || messageText.indexOf("The received token does not match the token inclusion requirement") < 0) {
				return;
			}
			// for SOAP messages...
			if (message instanceof SoapMessage) {
				Optional<Messaging> messagingOpt = getMessaging(message);
				if (messagingOpt.isPresent()) {
					Messaging msg = messagingOpt.get();
					
					// And not for UserMessage - only SignalMessage!
					if (!empty(msg.getUserMessage())) {
						return;
					}
					List<SignalMessage> signalMessageList = msg.getSignalMessage();
					if (empty(signalMessageList)) {
						return;
					}
					SignalMessage sigMsg = msg.getSignalMessage().get(0);
					if (sigMsg == null) {
						return;
					}
					
					// and SignalMessage is filled ONLY with Error - and no PullRequest or Receipt...
					if (sigMsg.getPullRequest() != null || sigMsg.getReceipt() != null) {
						return;
					}
					
					// TODO: Should we validate that MessageInfo refToMessageId/timestamp are correct?
					// sigMsg.getMessageInfo();
					
					// and there is at least one Error
					if (!empty(sigMsg.getError())) {
						List<Error> errorList = sigMsg.getError();
						Error error = errorList.get(0);
						// Similar to network.oxalis.as4.outbound.TransmissionResponseConverter.convert(TransmissionRequest, SOAPMessage)
						if (error != null) {
							// Extract error and throw a new exception with error description
							String errorDescription = error.getDescription() != null ? error.getDescription().getValue() : "No description";
							throw new OxalisUnsignedSignalErrorResponseException(
									error.getErrorDetail(),
									AS4ErrorCode.nameOf(error.getErrorCode()),
									AS4ErrorCode.Severity.nameOf(error.getSeverity()),
									errorDescription);
						}
					}
				}
			}
		}

	}

	private static boolean empty(List<?> l) {
		return l == null || l.isEmpty();
	}

	// Copied from network.oxalis.as4.inbound.AbstractSetPolicyInterceptor
	private final JAXBContext jaxbContext = Marshalling.getInstance();

	private Optional<Messaging> getMessaging(Message message) {
		SoapMessage soapMessage = (SoapMessage) message;
		Header header = soapMessage.getHeader(Constants.MESSAGING_QNAME);

		if (header == null) {
			return Optional.empty();
		}

		try {
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			Messaging messaging = unmarshaller.unmarshal((Node) header.getObject(), Messaging.class).getValue();
			return Optional.of(messaging);
		} catch (JAXBException e) {
			throw new Fault(e);
		}
	}
}