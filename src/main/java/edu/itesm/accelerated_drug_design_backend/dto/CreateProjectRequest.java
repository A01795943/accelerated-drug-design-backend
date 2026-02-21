package edu.itesm.accelerated_drug_design_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload for creating a project.
 * <ul>
 *   <li>When {@code targetDownloadFromWeb} is true: provide {@code targetName} as a PDB ID (e.g. "1ABC"); target is downloaded from https://files.rcsb.org/download/{pdb_id}.pdb</li>
 *   <li>When {@code targetDownloadFromWeb} is false: provide {@code target} (target PDB content in payload).</li>
 * </ul>
 * Same logic for complex: {@code complexDownloadFromWeb} + {@code complexName} (PDB ID) vs {@code complex} (content).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateProjectRequest {

	@NotBlank(message = "name is required")
	private String name;
	private String description;

	/** When true, target is fetched from web using {@code targetName}; when false, use {@code target}. */
	private boolean targetDownloadFromWeb;
	/** Name/URL of target when downloading from web. */
	private String targetName;
	/** Target content when not downloading from web. */
	private String target;

	/** When true, complex is fetched from web using {@code complexName}; when false, use {@code complex}. */
	private boolean complexDownloadFromWeb;
	/** Name/URL of complex when downloading from web. */
	private String complexName;
	/** Complex content when not downloading from web. */
	private String complex;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isTargetDownloadFromWeb() {
		return targetDownloadFromWeb;
	}

	public void setTargetDownloadFromWeb(boolean targetDownloadFromWeb) {
		this.targetDownloadFromWeb = targetDownloadFromWeb;
	}

	public String getTargetName() {
		return targetName;
	}

	public void setTargetName(String targetName) {
		this.targetName = targetName;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public boolean isComplexDownloadFromWeb() {
		return complexDownloadFromWeb;
	}

	public void setComplexDownloadFromWeb(boolean complexDownloadFromWeb) {
		this.complexDownloadFromWeb = complexDownloadFromWeb;
	}

	public String getComplexName() {
		return complexName;
	}

	public void setComplexName(String complexName) {
		this.complexName = complexName;
	}

	public String getComplex() {
		return complex;
	}

	public void setComplex(String complex) {
		this.complex = complex;
	}
}
