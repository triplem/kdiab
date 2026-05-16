# TreatmentResponse


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **string** |  | [default to undefined]
**userId** | **string** |  | [default to undefined]
**treatedAt** | **string** | When the treatment actually occurred | [default to undefined]
**createdAt** | **string** | When the record was entered into the system | [default to undefined]
**type** | [**TreatmentType**](TreatmentType.md) |  | [default to undefined]
**data** | [**TreatmentPayload**](TreatmentPayload.md) |  | [default to undefined]
**notes** | **string** | Optional free-text notes | [optional] [default to undefined]
**status** | **string** | Treatment status (ACTIVE or ARCHIVED) | [default to undefined]

## Example

```typescript
import { TreatmentResponse } from './api';

const instance: TreatmentResponse = {
    id,
    userId,
    treatedAt,
    createdAt,
    type,
    data,
    notes,
    status,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
