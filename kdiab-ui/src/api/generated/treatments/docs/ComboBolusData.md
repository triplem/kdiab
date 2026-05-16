# ComboBolusData

Split bolus — part immediate, part extended (COMBO_BOLUS)

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**insulin** | **number** | Total insulin dose in units | [default to undefined]
**splitNow** | **number** | Percentage delivered immediately | [default to undefined]
**splitExt** | **number** | Percentage delivered over the extended period | [default to undefined]
**duration** | **number** | Extended delivery duration in minutes | [default to undefined]

## Example

```typescript
import { ComboBolusData } from './api';

const instance: ComboBolusData = {
    insulin,
    splitNow,
    splitExt,
    duration,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
