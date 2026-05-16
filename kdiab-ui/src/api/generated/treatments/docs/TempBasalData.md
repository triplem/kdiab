# TempBasalData

Temporary basal rate change (TEMP_BASAL)

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**rate** | **number** | Basal rate in U/hr | [default to undefined]
**duration** | **number** | Duration in minutes | [default to undefined]
**absolute** | **boolean** | True &#x3D; absolute rate, false &#x3D; percentage of scheduled rate | [optional] [default to undefined]

## Example

```typescript
import { TempBasalData } from './api';

const instance: TempBasalData = {
    rate,
    duration,
    absolute,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
