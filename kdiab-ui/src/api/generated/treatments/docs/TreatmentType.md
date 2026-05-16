# TreatmentType

The type of treatment, which defines the expected structure of the data payload. Mapped to Nightscout eventType values where applicable: - BOLUS: Rapid-acting insulin dose (\"Meal Bolus\" / \"Bolus\") - BASAL: Long-acting basal insulin (\"Basal\") - CARBS: Carbohydrate intake only (\"Carbs Only\") - CORRECTION_BOLUS: Correction insulin dose (\"Correction Bolus\") - COMBO_BOLUS: Split bolus — part immediate, part extended (\"Combo Bolus\") - TEMP_BASAL: Temporary basal rate change (\"Temp Basal\") - EXERCISE: Physical activity (\"Exercise\") - NOTE: Free-text note (\"Note\") - PUMP_SUSPEND: Insulin pump suspended (\"Pump Suspend\") - SITE_CHANGE: Infusion site/cannula replacement (\"Site Change\") - SENSOR_INSERT: CGM sensor insertion (\"Sensor Insert\") - INSULIN_CHANGE: Insulin cartridge/pen replacement (\"Insulin Change\") - ACTIVITY: Physical activity log with name, duration (minutes), and intensity level (low/moderate/high) - HYPO_TREATMENT: Emergency fast-acting carbohydrate intake to correct hypoglycaemia (juice, dextrose) - DEVICE_STATUS: Pump and uploader status snapshot (reservoir units, battery level, client name) 

## Enum

* `Bolus` (value: `'BOLUS'`)

* `Basal` (value: `'BASAL'`)

* `Carbs` (value: `'CARBS'`)

* `CorrectionBolus` (value: `'CORRECTION_BOLUS'`)

* `ComboBolus` (value: `'COMBO_BOLUS'`)

* `TempBasal` (value: `'TEMP_BASAL'`)

* `Exercise` (value: `'EXERCISE'`)

* `Note` (value: `'NOTE'`)

* `PumpSuspend` (value: `'PUMP_SUSPEND'`)

* `SiteChange` (value: `'SITE_CHANGE'`)

* `SensorInsert` (value: `'SENSOR_INSERT'`)

* `InsulinChange` (value: `'INSULIN_CHANGE'`)

* `Activity` (value: `'ACTIVITY'`)

* `HypoTreatment` (value: `'HYPO_TREATMENT'`)

* `DeviceStatus` (value: `'DEVICE_STATUS'`)

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
