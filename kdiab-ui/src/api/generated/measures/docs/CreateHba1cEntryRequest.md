# CreateHba1cEntryRequest

Request body for recording a manual HbA1c lab result

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**measuredAt** | **string** | When the HbA1c was measured (ISO-8601) | [default to undefined]
**valuePercent** | **number** | HbA1c value as a percentage | [default to undefined]
**source** | [**HbA1cSource**](HbA1cSource.md) |  | [optional] [default to undefined]
**notes** | **string** | Optional notes | [optional] [default to undefined]

## Example

```typescript
import { CreateHba1cEntryRequest } from './api';

const instance: CreateHba1cEntryRequest = {
    measuredAt,
    valuePercent,
    source,
    notes,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
