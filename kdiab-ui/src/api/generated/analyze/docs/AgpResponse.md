# AgpResponse


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**bucketData** | [**Array&lt;AgpBucketData&gt;**](AgpBucketData.md) | 288 five-minute buckets covering a full day (minuteOfDay 0, 5, 10, …, 1435). | [default to undefined]
**totalReadingCount** | **number** | Total number of CGM readings used across all 5-minute buckets. | [optional] [default to undefined]
**sensorWearDays** | **number** | Number of distinct calendar days (in the patient\&#39;s local timezone) with at least one CGM reading. | [optional] [default to undefined]
**warnings** | **Array&lt;string&gt;** | Data quality warnings about CGM data sufficiency | [optional] [default to undefined]

## Example

```typescript
import { AgpResponse } from './api';

const instance: AgpResponse = {
    bucketData,
    totalReadingCount,
    sensorWearDays,
    warnings,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
