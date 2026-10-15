package edu.itesm.accelerated_drug_design_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreBaseUrlValidatorTest {

	private CoreBaseUrlValidator validator;

	@BeforeEach
	void setUp() {
		validator = new CoreBaseUrlValidator(
				"100.64.0.0/10,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8");
	}

	@Test
	void acceptsTailscaleAndRfc1918AndLoopback() {
		assertEquals("http://100.76.235.84:8000", validator.validateAndNormalize("http://100.76.235.84:8000/"));
		assertEquals("http://10.1.2.3:8000", validator.validateAndNormalize("http://10.1.2.3:8000"));
		assertEquals("https://192.168.1.10", validator.validateAndNormalize("https://192.168.1.10"));
		assertEquals("http://127.0.0.1:8000", validator.validateAndNormalize("http://127.0.0.1:8000"));
		assertEquals("http://localhost:8000", validator.validateAndNormalize("http://localhost:8000"));
	}

	@Test
	void rejectsPublicIpAndNonHttp() {
		IllegalArgumentException publicIp = assertThrows(
				IllegalArgumentException.class,
				() -> validator.validateAndNormalize("http://8.8.8.8:8000"));
		assertTrue(publicIp.getMessage().contains("allowed private CIDR"));

		IllegalArgumentException metadata = assertThrows(
				IllegalArgumentException.class,
				() -> validator.validateAndNormalize("http://169.254.169.254/"));
		assertTrue(metadata.getMessage().contains("allowed private CIDR"));

		assertThrows(IllegalArgumentException.class, () -> validator.validateAndNormalize("ftp://192.168.1.1"));
		assertThrows(IllegalArgumentException.class, () -> validator.validateAndNormalize("not-a-url"));
	}

	@Test
	void loopbackIpv6IsAllowed() throws Exception {
		assertTrue(validator.isAllowed(InetAddress.getByName("::1")));
	}
}
