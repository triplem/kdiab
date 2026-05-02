# CreateMeasureRequest


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**measuredAt** | **string** |  | [default to undefined]
**type** | [**MeasureType**](MeasureType.md) |  | [default to undefined]
**source** | [**MeasureSource**](MeasureSource.md) |  | [default to undefined]
**data** | **{ [key: string]: any; }** | Flexible JSON payload whose structure depends on the measure type | [default to undefined]

## Example

```typescript
import { CreateMeasureRequest } from './api';

const instance: CreateMeasureRequest = {
    measuredAt,
    type,
    source,
    data,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
