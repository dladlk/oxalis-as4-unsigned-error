# oxalis-as4-unsigned-error module

## Purpose

Drop this jar into your Oxalis-AS4 classpath and activate it to see unencrypted rejection messages from server access point

## Idea

The [OxalisUnsignedSignalErrorResponseModule](.blob/main/src/main/java/network/oxalis/as4/outbound/OxalisUnsignedSignalErrorResponseModule.java) tries to add to the default CXF Bus an InIntercepter:

https://github.com/dladlk/oxalis-as4-unsigned-error/blob/main/src/main/java/network/oxalis/as4/outbound/OxalisUnsignedSignalErrorResponseInInterceptor.java#L39-L99

In handleFault it checks if raised exception is PolicyException with texts "Soap Body is not SIGNED" AND "The received token does not match the token inclusion requirement", SignalMessage is not empty and has an Error filled in - and throws OxalisUnsignedSignalErrorResponseException with the contents from the first SignalMessage.Error of a SOAP Fault.

## Installation

Copy jar to classpath and activate by adding to your reference.conf or oxalis.conf:

```

oxalis.module.as4.unsigned = {
    class = network.oxalis.as4.outbound.OxalisUnsignedSignalErrorResponseModule
}
```
