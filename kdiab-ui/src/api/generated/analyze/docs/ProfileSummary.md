# ProfileSummary


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **string** |  | [default to undefined]
**status** | **string** |  | [default to undefined]
**name** | **string** |  | [default to undefined]
**createdAt** | **string** |  | [default to undefined]
**validFrom** | **string** | ISO-8601 timestamp of when the profile entered its current status. | [optional] [default to undefined]
**previousProfileId** | **string** |  | [optional] [default to undefined]
**activatedAt** | **string** | ISO-8601 timestamp of when the profile was activated. | [optional] [default to undefined]
**archivedAt** | **string** | ISO-8601 timestamp of when the profile was archived. | [optional] [default to undefined]

## Example

```typescript
import { ProfileSummary } from './api';

const instance: ProfileSummary = {
    id,
    status,
    name,
    createdAt,
    validFrom,
    previousProfileId,
    activatedAt,
    archivedAt,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
