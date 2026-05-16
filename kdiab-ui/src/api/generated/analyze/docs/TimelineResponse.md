# TimelineResponse


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**measures** | [**Array&lt;TimelineMeasure&gt;**](TimelineMeasure.md) |  | [default to undefined]
**treatments** | [**Array&lt;TimelineTreatment&gt;**](TimelineTreatment.md) |  | [default to undefined]
**errors** | **Array&lt;string&gt;** | Non-empty when one or more upstream services were unavailable. The timeline is returned with whatever data was successfully fetched.  | [optional] [default to undefined]

## Example

```typescript
import { TimelineResponse } from './api';

const instance: TimelineResponse = {
    measures,
    treatments,
    errors,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
