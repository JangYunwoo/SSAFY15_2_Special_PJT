-- Preserve the original prepare request; never infer legacy selections from a later quote.
ALTER TABLE payments ADD COLUMN prepare_crypto_asset_id BIGINT NULL;
