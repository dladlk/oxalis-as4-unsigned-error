package network.oxalis.as4.outbound;

import network.oxalis.as4.lang.AS4Error;
import network.oxalis.as4.util.AS4ErrorCode;
import network.oxalis.as4.util.AS4ErrorCode.Severity;

// Extends RuntimeException to be able to throw from Interceptor without wrapping by RuntimeException, but implement AS4Error
// similar to OxalisAs4TransmissionException
public class OxalisUnsignedSignalErrorResponseException extends RuntimeException implements AS4Error {

	private static final long serialVersionUID = 4629128621507529575L;
	
    private AS4ErrorCode errorCode = AS4ErrorCode.EBMS_0004;
    private AS4ErrorCode.Severity severity = AS4ErrorCode.Severity.ERROR;
	private String errorDescription;
	
	public OxalisUnsignedSignalErrorResponseException(String message, AS4ErrorCode errorCode, Severity severity, String errorDescription) {
		super(message + ": " + errorDescription);
        this.severity = severity;
		this.errorDescription = errorDescription;
	}

	public String getErrorDescription() {
		return errorDescription;
	}

	public AS4ErrorCode getErrorCode() {
		return errorCode;
	}

	public AS4ErrorCode.Severity getSeverity() {
		return severity;
	}

	@Override
	public Exception getException() {
		return this;
	}
	
}