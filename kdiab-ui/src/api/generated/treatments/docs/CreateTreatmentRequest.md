# CreateTreatmentRequest


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**treatedAt** | **string** | When the treatment actually occurred | [default to undefined]
**type** | [**TreatmentType**](TreatmentType.md) |  | [default to undefined]
**data** | [**TreatmentPayload**](TreatmentPayload.md) |  | [default to undefined]
**notes** | **string** | Optional free-text notes | [optional] [default to undefined]

## Example

```typescript
import { CreateTreatmentRequest } from './api';

const instance: CreateTreatmentRequest = {
    treatedAt,
    type,
    data,
    notes,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
