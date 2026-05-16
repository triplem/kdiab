# HbA1cEntryResponse

A lab-measured or CGM-estimated HbA1c entry

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **string** | Unique identifier for the entry | [default to undefined]
**userId** | **string** | The user this entry belongs to | [default to undefined]
**measuredAt** | **string** | When the HbA1c was measured (ISO-8601) | [default to undefined]
**valuePercent** | **number** | HbA1c value as a percentage (e.g. 7.2 means 7.2%) | [default to undefined]
**source** | [**HbA1cSource**](HbA1cSource.md) |  | [default to undefined]
**notes** | **string** | Optional notes about the measurement | [optional] [default to undefined]
**createdAt** | **string** | When this record was created | [default to undefined]

## Example

```typescript
import { HbA1cEntryResponse } from './api';

const instance: HbA1cEntryResponse = {
    id,
    userId,
    measuredAt,
    valuePercent,
    source,
    notes,
    createdAt,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
