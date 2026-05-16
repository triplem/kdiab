# CreateProfileRequest


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **string** |  | [default to undefined]
**insulinType** | **string** |  | [default to undefined]
**durationOfAction** | **number** |  | [default to undefined]
**proposalReason** | **string** | Optional clinical rationale for the proposed profile change (doctor-only). | [optional] [default to undefined]
**analysisLow** | **number** | Lower TIR threshold for analytics (mg/dL). Defaults to 70.0 when null. | [optional] [default to undefined]
**analysisHigh** | **number** | Upper TIR threshold for analytics (mg/dL). Defaults to 180.0 when null. | [optional] [default to undefined]
**carbAbsorptionRateGPerHour** | **number** | Default carb absorption rate in grams per hour. Used for COB calculation when a treatment does not specify an explicit absorptionTime. Defaults to 20.0 when null.  | [optional] [default to undefined]
**basal** | [**Array&lt;BasalSegment&gt;**](BasalSegment.md) |  | [optional] [default to undefined]
**icr** | [**Array&lt;IcrSegment&gt;**](IcrSegment.md) |  | [optional] [default to undefined]
**isf** | [**Array&lt;IsfSegment&gt;**](IsfSegment.md) |  | [optional] [default to undefined]
**targets** | [**Array&lt;TargetSegment&gt;**](TargetSegment.md) |  | [optional] [default to undefined]

## Example

```typescript
import { CreateProfileRequest } from './api';

const instance: CreateProfileRequest = {
    name,
    insulinType,
    durationOfAction,
    proposalReason,
    analysisLow,
    analysisHigh,
    carbAbsorptionRateGPerHour,
    basal,
    icr,
    isf,
    targets,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
