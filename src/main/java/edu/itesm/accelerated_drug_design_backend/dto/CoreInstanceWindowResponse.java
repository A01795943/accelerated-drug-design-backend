package edu.itesm.accelerated_drug_design_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

public record CoreInstanceWindowResponse(
		Long id,
		Integer dayOfWeek,
		@JsonFormat(pattern = "HH:mm")
		LocalTime startTime,
		@JsonFormat(pattern = "HH:mm")
		LocalTime endTime
) {}
