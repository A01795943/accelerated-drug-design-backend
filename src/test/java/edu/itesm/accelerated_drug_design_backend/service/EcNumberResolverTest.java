package edu.itesm.accelerated_drug_design_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EcNumberResolverTest {

	private static final String PDB_ID = "2PTN";
	private static final String DATA_API_URL =
			"https://data.rcsb.org/rest/v1/core/polymer_entity/2PTN/1";

	private RestTemplate restTemplate;
	private EcNumberResolver resolver;

	@BeforeEach
	void setUp() {
		restTemplate = mock(RestTemplate.class);
		resolver = new EcNumberResolver(restTemplate);
	}

	@Test
	void compndFallbackResolvesFirstEc() {
		Optional<EcNumberResolver.EcInfo> info = resolver.resolve(null, """
				COMPND    MOL_ID: 1;
				COMPND   2 MOLECULE: TRYPSIN;
				COMPND   5 EC: 3.4.21.4;
				""");

		assertTrue(info.isPresent());
		assertEquals("3.4.21.4", info.get().ecNumber());
		assertNull(info.get().ecLineage());
		verify(restTemplate, never()).getForObject(anyString(), eq(String.class));
	}

	@Test
	void pdbWithoutEcReturnsEmpty() {
		Optional<EcNumberResolver.EcInfo> info = resolver.resolve(null, """
				COMPND    MOL_ID: 1;
				COMPND   2 MOLECULE: PROGRAMMED CELL DEATH 1 LIGAND 1;
				""");

		assertTrue(info.isEmpty());
	}

	@Test
	void dataApiEcWinsOverCompnd() {
		when(restTemplate.getForObject(DATA_API_URL, String.class)).thenReturn("""
				{"rcsb_polymer_entity":{"rcsb_ec_number":"5.3.1.1","rcsb_ec_lineage":[{"depth":1,"name":"Isomerases"}]}}
				""");

		Optional<EcNumberResolver.EcInfo> info = resolver.resolve(PDB_ID, """
				COMPND   5 EC: 3.4.21.4;
				""");

		assertTrue(info.isPresent());
		assertEquals("5.3.1.1", info.get().ecNumber());
		assertEquals("Isomerases", info.get().ecLineage());
	}

	@Test
	void livePolymerEntityShapeResolvesEcAndLineage() {
		when(restTemplate.getForObject(DATA_API_URL, String.class)).thenReturn("""
				{"rcsb_polymer_entity":{
				  "pdbx_ec":"3.4.21.4",
				  "rcsb_ec_lineage":[
				    {"depth":1,"id":"3","name":"Hydrolases"},
				    {"depth":4,"id":"3.4.21.4","name":"trypsin"}
				  ]
				}}
				""");

		Optional<EcNumberResolver.EcInfo> info = resolver.resolve(PDB_ID, null);

		assertTrue(info.isPresent());
		assertEquals("3.4.21.4", info.get().ecNumber());
		assertEquals("Hydrolases > trypsin", info.get().ecLineage());
	}

	@Test
	void dataApi404FallsBackToCompnd() {
		when(restTemplate.getForObject(DATA_API_URL, String.class)).thenThrow(
				HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), new byte[0], null));

		Optional<EcNumberResolver.EcInfo> info = resolver.resolve(PDB_ID, "COMPND   5 EC: 3.4.21.4;\n");

		assertTrue(info.isPresent());
		assertEquals("3.4.21.4", info.get().ecNumber());
	}

	@Test
	void dataApiTimeoutFallsBackToCompnd() {
		when(restTemplate.getForObject(DATA_API_URL, String.class)).thenThrow(
				new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));

		Optional<EcNumberResolver.EcInfo> info = resolver.resolve(PDB_ID, "COMPND   5 EC: 3.4.21.4;\n");

		assertTrue(info.isPresent());
		assertEquals("3.4.21.4", info.get().ecNumber());
	}

	@Test
	void partialEcIsAccepted() {
		Optional<EcNumberResolver.EcInfo> info = resolver.resolve(null, "COMPND   4 EC: 3.4.-.-;\n");

		assertTrue(info.isPresent());
		assertEquals("3.4.-.-", info.get().ecNumber());
	}

	@Test
	void naAndAllDashEcAreDiscarded() {
		assertTrue(resolver.resolve(null, "COMPND   4 EC: N/A;\n").isEmpty());
		assertTrue(resolver.resolve(null, "COMPND   4 EC: -.-.-.-;\n").isEmpty());
	}

	@Test
	void headerExtractsFourCharacterId() {
		String header = "HEADER" + " ".repeat(56) + "2ptn";
		assertEquals(66, header.length());

		assertEquals("2PTN", resolver.extractPdbIdFromHeader(header).orElseThrow());
	}

	@Test
	void truncatedHeaderReturnsEmpty() {
		assertTrue(resolver.extractPdbIdFromHeader("HEADER    HYDROLASE").isEmpty());
	}
}
