# MeasureType

The type of health measurement, which defines the expected structure of the data payload: - CGM: Continuous Glucose Monitor reading in mg/dL (e.g. `{\"value\": 120, \"trend\": \"Flat\"}`) - BGM: Blood Glucose Meter reading in mg/dL (e.g. `{\"value\": 110}`) - BLOOD_PRESSURE: (e.g. `{\"systolic\": 120, \"diastolic\": 80}`) — two values, both in mmHg - WEIGHT: (e.g. `{\"value\": 75.5, \"unit\": \"kg\"}`) - PULSE: Heart rate in bpm (e.g. `{\"value\": 72}`) - BG_CHECK: Manual blood glucose check, stored in mg/dL (e.g. `{\"value\": 100}`) - KETONE_CHECK: Blood or urine ketone measurement in mmol/L (e.g. `{\"value\": 1.5, \"method\": \"blood\"}`). `method` is `blood` or `urine`. 

## Enum

* `Cgm` (value: `'CGM'`)

* `Bgm` (value: `'BGM'`)

* `BloodPressure` (value: `'BLOOD_PRESSURE'`)

* `Weight` (value: `'WEIGHT'`)

* `Pulse` (value: `'PULSE'`)

* `BgCheck` (value: `'BG_CHECK'`)

* `KetoneCheck` (value: `'KETONE_CHECK'`)

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
