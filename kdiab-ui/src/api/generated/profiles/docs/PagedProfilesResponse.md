# PagedProfilesResponse

Paginated list of profiles

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**items** | [**Array&lt;Profile&gt;**](Profile.md) |  | [default to undefined]
**page** | **number** | Current page number (0-based) | [default to undefined]
**size** | **number** | Number of items per page | [default to undefined]
**totalCount** | **number** | Total number of profiles matching the query | [default to undefined]

## Example

```typescript
import { PagedProfilesResponse } from './api';

const instance: PagedProfilesResponse = {
    items,
    page,
    size,
    totalCount,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
