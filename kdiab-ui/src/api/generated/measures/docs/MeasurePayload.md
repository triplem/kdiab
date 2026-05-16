# MeasurePayload

JSON payload whose structure depends on the measure type. Use the sibling `type` field as the discriminator.

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**value** | **number** | Ketone level in mmol/L | [default to undefined]
**trend** | **string** | CGM directional trend arrow | [optional] [default to undefined]
**systolic** | **number** | Systolic pressure in mmHg | [default to undefined]
**diastolic** | **number** | Diastolic pressure in mmHg | [default to undefined]
**unit** | **string** | Weight unit | [optional] [default to undefined]
**method** | **string** | Measurement method | [optional] [default to undefined]

## Example

```typescript
import { MeasurePayload } from './api';

const instance: MeasurePayload = {
    value,
    trend,
    systolic,
    diastolic,
    unit,
    method,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
