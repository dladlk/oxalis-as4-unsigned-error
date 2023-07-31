package network.oxalis.as4.outbound;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;

import lombok.extern.slf4j.Slf4j;
import network.oxalis.commons.guice.OxalisModule;

@Slf4j
public class OxalisUnsignedSignalErrorResponseModule extends OxalisModule {

	private static final int SLEEP_INTERVAL = 100;
	private static final int MAX_SECONDS_RETRY_TO_TRY = 300;
	private static final int MAX_ATTEMPTS = 1000 / SLEEP_INTERVAL * MAX_SECONDS_RETRY_TO_TRY;

	@Override
	protected void configure() {
		Thread t = new Thread("OxalisUnsignedSignalErrorResponseInInterceptor installer") {
			public void run() {
				int count = 0;
				while (!installInterceptor()) {
					try {
						Thread.sleep(SLEEP_INTERVAL);
					} catch (InterruptedException e) {
						break;
					}
					count++;
					if (count >= MAX_ATTEMPTS) {
						log.warn("Failed to find a created Bus to install OxalisUnsignedSignalErrorResponseModule after " + count + " attempts, give up");
						break;
					}
				}
				log.warn("Thread stopped after " + count + " attempts");
			}
		};
		log.warn("Start temporary thread to wait for CXF Bus initialization to register OxalisUnsignedSignalErrorResponseInInterceptor, repeat each " + SLEEP_INTERVAL + " ms up to " + MAX_ATTEMPTS + " times");
		t.start();
	}

	private static final boolean installInterceptor() {
		Bus bus = BusFactory.getDefaultBus(false);
		if (bus != null) {
			bus.getInInterceptors().add(new OxalisUnsignedSignalErrorResponseInInterceptor());
			log.warn("Installed OxalisUnsignedSignalErrorResponseInInterceptor so unsigned error responses will be parsed anyway");
			return true;
		}
		return false;
	}

}
