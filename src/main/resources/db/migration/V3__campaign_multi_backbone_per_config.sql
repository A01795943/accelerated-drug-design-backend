-- Option A (least disruptive): campaign_backbones already stores one row per physical
-- backbone with contig/hotspots/chains_to_remove inline. Multiple rows share the same
-- logical BackboneConfig via config_index; numBackbones requested is stored in
-- backbone_configs_json on the campaign (see DataGenerationCampaignService).
--
-- Max 20 backbones per config is enforced in the API (CreateDataGenerationCampaignRequest).

ALTER TABLE campaign_backbones ADD COLUMN config_index INT NOT NULL DEFAULT 0;
ALTER TABLE campaign_backbones ADD COLUMN created_at TIMESTAMP NULL;
ALTER TABLE campaign_backbones ADD COLUMN updated_at TIMESTAMP NULL;

ALTER TABLE data_generation_campaigns ADD COLUMN backbone_configs_json TEXT;

-- Legacy rows: each sort_order was its own logical config.
UPDATE campaign_backbones SET config_index = sort_order;
