package edu.itesm.accelerated_drug_design_backend.dto;

import java.util.List;

public class GenerateResult {
    private final String contig;
    private final String hotspots;
    private final List<String> chainsToRemove;

    public GenerateResult(String contig, String hotspots, List<String> chainsToRemove) {
        this.contig = contig;
        this.hotspots = hotspots;
        this.chainsToRemove = chainsToRemove;
    }

    public String getContig() { return contig; }
    public String getHotspots() { return hotspots; }
    public List<String> getChainsToRemove() { return chainsToRemove; }

    public String getChainsToRemoveCsv() {
        return String.join(",", chainsToRemove);
    }
}