package edu.itesm.accelerated_drug_design_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CoreInstanceWindowRequest(
		@NotNull
		@Min(1)
		@Max(7)
		Integer dayOfWeek,
		@NotNull
		@JsonFormat(pattern = "HH:mm")
		LocalTime startTime,
		@NotNull
		@JsonFormat(pattern = "HH:mm")
		LocalTime endTime
) {}
