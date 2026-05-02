# MeasureResponse


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **string** |  | [default to undefined]
**userId** | **string** |  | [default to undefined]
**measuredAt** | **string** |  | [default to undefined]
**createdAt** | **string** |  | [default to undefined]
**type** | [**MeasureType**](MeasureType.md) |  | [default to undefined]
**source** | [**MeasureSource**](MeasureSource.md) |  | [default to undefined]
**data** | **{ [key: string]: any; }** | Flexible JSON payload whose structure depends on the measure type | [default to undefined]
**status** | [**MeasureStatus**](MeasureStatus.md) |  | [default to undefined]

## Example

```typescript
import { MeasureResponse } from './api';

const instance: MeasureResponse = {
    id,
    userId,
    measuredAt,
    createdAt,
    type,
    source,
    data,
    status,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
