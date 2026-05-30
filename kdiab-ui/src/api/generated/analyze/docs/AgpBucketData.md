# AgpBucketData


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**minuteOfDay** | **number** | Start of the 5-minute bucket expressed as minutes since midnight (bucketIndex * 5). Values are 0, 5, 10, …, 1435. | [default to undefined]
**p10** | **number** | 10th percentile glucose value (mg/dL) for this bucket. Null when the bucket has no readings. | [optional] [default to undefined]
**p25** | **number** | 25th percentile glucose value (mg/dL) for this bucket. Null when the bucket has no readings. | [optional] [default to undefined]
**median** | **number** | Median (50th percentile) glucose value (mg/dL) for this bucket. Null when the bucket has no readings. | [optional] [default to undefined]
**p75** | **number** | 75th percentile glucose value (mg/dL) for this bucket. Null when the bucket has no readings. | [optional] [default to undefined]
**p90** | **number** | 90th percentile glucose value (mg/dL) for this bucket. Null when the bucket has no readings. | [optional] [default to undefined]
**count** | **number** | Number of CGM readings in this bucket. | [optional] [default to undefined]

## Example

```typescript
import { AgpBucketData } from './api';

const instance: AgpBucketData = {
    minuteOfDay,
    p10,
    p25,
    median,
    p75,
    p90,
    count,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
