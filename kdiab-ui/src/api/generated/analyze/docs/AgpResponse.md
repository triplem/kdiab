# AgpResponse


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**hourlyData** | [**Array&lt;AgpHourlyData&gt;**](AgpHourlyData.md) |  | [default to undefined]
**totalReadingCount** | **number** | Total number of CGM readings used across all hourly buckets. | [optional] [default to undefined]
**sensorWearDays** | **number** | Number of distinct calendar days (UTC) with at least one CGM reading. | [optional] [default to undefined]
**warnings** | **Array&lt;string&gt;** | Data quality warnings about CGM data sufficiency | [optional] [default to undefined]

## Example

```typescript
import { AgpResponse } from './api';

const instance: AgpResponse = {
    hourlyData,
    totalReadingCount,
    sensorWearDays,
    warnings,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
