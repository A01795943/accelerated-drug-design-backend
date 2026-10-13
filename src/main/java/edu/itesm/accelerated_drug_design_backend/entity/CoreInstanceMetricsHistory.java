package edu.itesm.accelerated_drug_design_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "core_instance_metrics_history")
public class CoreInstanceMetricsHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "core_instance_id", nullable = false)
	private Long coreInstanceId;

	@Column(name = "collected_at", nullable = false)
	private Instant collectedAt;

	@Column(name = "cpu_percent")
	private Double cpuPercent;

	@Column(name = "mem_percent")
	private Double memPercent;

	@Column(name = "gpu_util_percent")
	private Double gpuUtilPercent;

	@Column(name = "gpu_mem_percent")
	private Double gpuMemPercent;

	@Column(name = "disk_percent")
	private Double diskPercent;

	@Column(name = "running_count")
	private Integer runningCount;

	@Column(name = "net_sent_per_sec")
	private Double netSentPerSec;

	@Column(name = "net_recv_per_sec")
	private Double netRecvPerSec;

	@Column(name = "gpu_mem_used_mb")
	private Double gpuMemUsedMb;

	@Column(name = "gpu_mem_total_mb")
	private Double gpuMemTotalMb;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getCoreInstanceId() {
		return coreInstanceId;
	}

	public void setCoreInstanceId(Long coreInstanceId) {
		this.coreInstanceId = coreInstanceId;
	}

	public Instant getCollectedAt() {
		return collectedAt;
	}

	public void setCollectedAt(Instant collectedAt) {
		this.collectedAt = collectedAt;
	}

	public Double getCpuPercent() {
		return cpuPercent;
	}

	public void setCpuPercent(Double cpuPercent) {
		this.cpuPercent = cpuPercent;
	}

	public Double getMemPercent() {
		return memPercent;
	}

	public void setMemPercent(Double memPercent) {
		this.memPercent = memPercent;
	}

	public Double getGpuUtilPercent() {
		return gpuUtilPercent;
	}

	public void setGpuUtilPercent(Double gpuUtilPercent) {
		this.gpuUtilPercent = gpuUtilPercent;
	}

	public Double getGpuMemPercent() {
		return gpuMemPercent;
	}

	public void setGpuMemPercent(Double gpuMemPercent) {
		this.gpuMemPercent = gpuMemPercent;
	}

	public Double getDiskPercent() {
		return diskPercent;
	}

	public void setDiskPercent(Double diskPercent) {
		this.diskPercent = diskPercent;
	}

	public Integer getRunningCount() {
		return runningCount;
	}

	public void setRunningCount(Integer runningCount) {
		this.runningCount = runningCount;
	}

	public Double getNetSentPerSec() {
		return netSentPerSec;
	}

	public void setNetSentPerSec(Double netSentPerSec) {
		this.netSentPerSec = netSentPerSec;
	}

	public Double getNetRecvPerSec() {
		return netRecvPerSec;
	}

	public void setNetRecvPerSec(Double netRecvPerSec) {
		this.netRecvPerSec = netRecvPerSec;
	}

	public Double getGpuMemUsedMb() {
		return gpuMemUsedMb;
	}

	public void setGpuMemUsedMb(Double gpuMemUsedMb) {
		this.gpuMemUsedMb = gpuMemUsedMb;
	}

	public Double getGpuMemTotalMb() {
		return gpuMemTotalMb;
	}

	public void setGpuMemTotalMb(Double gpuMemTotalMb) {
		this.gpuMemTotalMb = gpuMemTotalMb;
	}
}
