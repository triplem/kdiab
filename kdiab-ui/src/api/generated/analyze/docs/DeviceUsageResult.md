# DeviceUsageResult


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **string** | The user this result belongs to | [default to undefined]
**avgSensorDays** | **number** | Average sensor wear duration in days. Null if fewer than 2 SENSOR_INSERT events exist. | [optional] [default to undefined]
**stddevSensorDays** | **number** | Population standard deviation of sensor wear duration in days. | [optional] [default to undefined]
**avgCatheterDays** | **number** | Average catheter/cannula wear duration in days. Null if fewer than 2 SITE_CHANGE events exist. | [optional] [default to undefined]
**stddevCatheterDays** | **number** | Population standard deviation of catheter wear duration in days. | [optional] [default to undefined]
**avgReservoirDays** | **number** | Average reservoir/insulin cartridge wear duration in days. Null if fewer than 2 INSULIN_CHANGE events exist. | [optional] [default to undefined]
**stddevReservoirDays** | **number** | Population standard deviation of reservoir wear duration in days. | [optional] [default to undefined]
**avgBatteryDays** | **number** | Average pump battery wear duration in days. Null if fewer than 2 PUMP_BATTERY_CHANGE events exist. | [optional] [default to undefined]
**stddevBatteryDays** | **number** | Population standard deviation of battery wear duration in days. | [optional] [default to undefined]

## Example

```typescript
import { DeviceUsageResult } from './api';

const instance: DeviceUsageResult = {
    userId,
    avgSensorDays,
    stddevSensorDays,
    avgCatheterDays,
    stddevCatheterDays,
    avgReservoirDays,
    stddevReservoirDays,
    avgBatteryDays,
    stddevBatteryDays,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
