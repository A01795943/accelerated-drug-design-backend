package edu.itesm.accelerated_drug_design_backend.dto;

/** Id and status only, so job validation does not load LONGTEXT columns. */
public record JobStatusView(Long id, String status) {}
