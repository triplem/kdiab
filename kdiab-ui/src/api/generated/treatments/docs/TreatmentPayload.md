# TreatmentPayload

JSON payload whose structure depends on the treatment type. Use the sibling `type` field as the discriminator. All `duration` fields are in minutes.

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**insulin** | **number** | Total insulin dose in units | [default to undefined]
**insulinType** | **string** | New insulin type loaded | [optional] [default to undefined]
**duration** | **number** | Duration in minutes | [default to undefined]
**carbs** | **number** | Fast-acting carbohydrate amount in grams | [default to undefined]
**absorptionTime** | **number** | Estimated absorption time in hours | [optional] [default to undefined]
**splitNow** | **number** | Percentage delivered immediately | [default to undefined]
**splitExt** | **number** | Percentage delivered over the extended period | [default to undefined]
**rate** | **number** | Basal rate in U/hr | [default to undefined]
**absolute** | **boolean** | True &#x3D; absolute rate, false &#x3D; percentage of scheduled rate | [optional] [default to undefined]
**intensity** | **string** | Activity intensity level | [optional] [default to undefined]
**text** | **string** | Note content | [default to undefined]
**reason** | **string** | Context for the hypo treatment | [optional] [default to undefined]
**location** | **string** | Insertion site location | [optional] [default to undefined]
**sensor** | **string** | CGM sensor model/brand | [optional] [default to undefined]
**name** | **string** | Activity name | [default to undefined]
**device** | **string** | Name and version of the uploading client application | [default to undefined]
**pumpName** | **string** | Pump model identifier as reported by the client | [optional] [default to undefined]
**reservoirUnits** | **number** | Insulin units remaining in the pump reservoir | [optional] [default to undefined]
**batteryLevel** | **number** | Pump battery level as a percentage (0–100) | [optional] [default to undefined]
**pumpConnected** | **boolean** | Whether the pump was connected at the time of the status report | [optional] [default to undefined]

## Example

```typescript
import { TreatmentPayload } from './api';

const instance: TreatmentPayload = {
    insulin,
    insulinType,
    duration,
    carbs,
    absorptionTime,
    splitNow,
    splitExt,
    rate,
    absolute,
    intensity,
    text,
    reason,
    location,
    sensor,
    name,
    device,
    pumpName,
    reservoirUnits,
    batteryLevel,
    pumpConnected,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
