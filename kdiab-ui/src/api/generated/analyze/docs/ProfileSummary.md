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
**insulinType** | **string** | Name of the insulin used (e.g. NovoRapid). | [optional] [default to undefined]
**durationOfAction** | **number** | Duration of insulin action in minutes. | [optional] [default to undefined]
**basal** | [**Array&lt;BasalSegment&gt;**](BasalSegment.md) | Basal rate schedule segments. | [optional] [default to undefined]
**icr** | [**Array&lt;RatioSegment&gt;**](RatioSegment.md) | Insulin-to-carb ratio schedule. | [optional] [default to undefined]
**isf** | [**Array&lt;RatioSegment&gt;**](RatioSegment.md) | Insulin sensitivity factor schedule. | [optional] [default to undefined]
**targets** | [**Array&lt;TargetSegment&gt;**](TargetSegment.md) | Blood glucose target ranges. | [optional] [default to undefined]

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
    insulinType,
    durationOfAction,
    basal,
    icr,
    isf,
    targets,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
