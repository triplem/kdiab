# Hba1cResponse


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**hba1c** | **number** | Estimated HbA1c in % (DCCT formula). Null if no CGM readings. | [optional] [default to undefined]
**meanGlucoseMgDl** | **number** | Mean CGM glucose in mg/dL over the period. | [optional] [default to undefined]
**readingCount** | **number** | Total number of CGM readings used. | [default to undefined]
**tir** | [**TirBreakdown**](TirBreakdown.md) |  | [default to undefined]
**warnings** | **Array&lt;string&gt;** | Data quality warnings about CGM data sufficiency | [optional] [default to undefined]

## Example

```typescript
import { Hba1cResponse } from './api';

const instance: Hba1cResponse = {
    hba1c,
    meanGlucoseMgDl,
    readingCount,
    tir,
    warnings,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
