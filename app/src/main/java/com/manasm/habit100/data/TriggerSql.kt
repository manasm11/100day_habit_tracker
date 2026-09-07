package com.manasm.habit100.data

object TriggerSql {
    val INSERT_GUARD = "CREATE TRIGGER trg_single_slot_insert BEFORE INSERT ON habits WHEN NEW.status IN ('forming','tuning_up') AND (SELECT COUNT(*) FROM habits WHERE status IN ('forming','tuning_up')) > 0 BEGIN SELECT RAISE(ABORT, 'single active slot violated'); END;"
    val UPDATE_GUARD = "CREATE TRIGGER trg_single_slot_update BEFORE UPDATE OF status ON habits WHEN NEW.status IN ('forming','tuning_up') AND (SELECT COUNT(*) FROM habits WHERE status IN ('forming','tuning_up') AND id <> NEW.id) > 0 BEGIN SELECT RAISE(ABORT, 'single active slot violated'); END;"
}
