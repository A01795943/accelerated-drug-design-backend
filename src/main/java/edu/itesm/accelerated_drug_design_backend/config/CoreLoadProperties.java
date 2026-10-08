package edu.itesm.accelerated_drug_design_backend.config;

import edu.itesm.accelerated_drug_design_backend.service.CoreLoadPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.core.load")
public class CoreLoadProperties {

	private double cpuEnter = 90;
	private double memoryEnter = 90;
	private double gpuUtilEnter = 95;
	private double gpuMemEnter = 90;
	private double diskEnter = 90;
	private double cpuRecover = 80;
	private double memoryRecover = 80;
	private double gpuUtilRecover = 80;
	private double gpuMemRecover = 80;
	private double diskRecover = 85;

	public CoreLoadPolicy.Thresholds enter() {
		return new CoreLoadPolicy.Thresholds(
				cpuEnter, memoryEnter, gpuUtilEnter, gpuMemEnter, diskEnter);
	}

	public CoreLoadPolicy.Thresholds recover() {
		return new CoreLoadPolicy.Thresholds(
				cpuRecover, memoryRecover, gpuUtilRecover, gpuMemRecover, diskRecover);
	}

	public double getCpuEnter() { return cpuEnter; }
	public void setCpuEnter(double cpuEnter) { this.cpuEnter = cpuEnter; }
	public double getMemoryEnter() { return memoryEnter; }
	public void setMemoryEnter(double memoryEnter) { this.memoryEnter = memoryEnter; }
	public double getGpuUtilEnter() { return gpuUtilEnter; }
	public void setGpuUtilEnter(double gpuUtilEnter) { this.gpuUtilEnter = gpuUtilEnter; }
	public double getGpuMemEnter() { return gpuMemEnter; }
	public void setGpuMemEnter(double gpuMemEnter) { this.gpuMemEnter = gpuMemEnter; }
	public double getDiskEnter() { return diskEnter; }
	public void setDiskEnter(double diskEnter) { this.diskEnter = diskEnter; }
	public double getCpuRecover() { return cpuRecover; }
	public void setCpuRecover(double cpuRecover) { this.cpuRecover = cpuRecover; }
	public double getMemoryRecover() { return memoryRecover; }
	public void setMemoryRecover(double memoryRecover) { this.memoryRecover = memoryRecover; }
	public double getGpuUtilRecover() { return gpuUtilRecover; }
	public void setGpuUtilRecover(double gpuUtilRecover) { this.gpuUtilRecover = gpuUtilRecover; }
	public double getGpuMemRecover() { return gpuMemRecover; }
	public void setGpuMemRecover(double gpuMemRecover) { this.gpuMemRecover = gpuMemRecover; }
	public double getDiskRecover() { return diskRecover; }
	public void setDiskRecover(double diskRecover) { this.diskRecover = diskRecover; }
}
