# Profile


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **string** |  | [default to undefined]
**userId** | **string** |  | [default to undefined]
**previousProfileId** | **string** |  | [optional] [default to undefined]
**name** | **string** |  | [default to undefined]
**insulinType** | **string** |  | [default to undefined]
**units** | **string** | Blood glucose unit system used for ISF and target segments | [optional] [default to UnitsEnum_MgDL]
**durationOfAction** | **number** | Duration of insulin action in minutes | [default to undefined]
**timeZone** | **string** | IANA timezone identifier for segment times (e.g. \&quot;Europe/Berlin\&quot;) | [optional] [default to undefined]
**status** | **string** |  | [default to undefined]
**createdAt** | **string** |  | [optional] [default to undefined]
**validFrom** | **string** | ISO-8601 timestamp of when the profile entered its current status. | [optional] [default to undefined]
**activatedAt** | **string** | ISO-8601 timestamp of when the profile was activated (entered ACTIVE status). | [optional] [default to undefined]
**archivedAt** | **string** | ISO-8601 timestamp of when the profile was archived (entered ARCHIVED status). | [optional] [default to undefined]
**proposalReason** | **string** | Optional clinical rationale for the proposed profile change (doctor-only). | [optional] [default to undefined]
**createdBy** | **string** | UUID of the doctor who created this profile proposal (populated server-side for PROPOSED profiles). | [optional] [default to undefined]
**rejectionReason** | **string** | Optional reason provided by the patient when rejecting a proposed profile. | [optional] [default to undefined]
**analysisLow** | **number** | Lower TIR threshold for analytics (mg/dL). Defaults to 70.0 when null. | [optional] [default to undefined]
**analysisHigh** | **number** | Upper TIR threshold for analytics (mg/dL). Defaults to 180.0 when null. | [optional] [default to undefined]
**carbAbsorptionRateGPerHour** | **number** | Default carb absorption rate in grams per hour, used for carbs-on-board (COB) calculation when a treatment does not specify an explicit absorptionTime. Typical values: 10 g/hr (slow/high-fat meal), 20 g/hr (mixed meal), 40 g/hr (fast-acting carbs / juice). Defaults to 20.0 when null.  | [optional] [default to undefined]
**basal** | [**Array&lt;BasalSegment&gt;**](BasalSegment.md) |  | [optional] [default to undefined]
**icr** | [**Array&lt;IcrSegment&gt;**](IcrSegment.md) |  | [optional] [default to undefined]
**isf** | [**Array&lt;IsfSegment&gt;**](IsfSegment.md) |  | [optional] [default to undefined]
**targets** | [**Array&lt;TargetSegment&gt;**](TargetSegment.md) |  | [optional] [default to undefined]

## Example

```typescript
import { Profile } from './api';

const instance: Profile = {
    id,
    userId,
    previousProfileId,
    name,
    insulinType,
    units,
    durationOfAction,
    timeZone,
    status,
    createdAt,
    validFrom,
    activatedAt,
    archivedAt,
    proposalReason,
    createdBy,
    rejectionReason,
    analysisLow,
    analysisHigh,
    carbAbsorptionRateGPerHour,
    basal,
    icr,
    isf,
    targets,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
