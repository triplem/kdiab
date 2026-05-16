# UpdateTreatmentRequest


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**treatedAt** | **string** | When the treatment actually occurred | [default to undefined]
**data** | [**TreatmentPayload**](TreatmentPayload.md) |  | [default to undefined]
**notes** | **string** | Optional free-text notes | [optional] [default to undefined]

## Example

```typescript
import { UpdateTreatmentRequest } from './api';

const instance: UpdateTreatmentRequest = {
    treatedAt,
    data,
    notes,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
