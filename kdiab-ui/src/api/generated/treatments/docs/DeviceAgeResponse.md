# DeviceAgeResponse

Most recent timestamp for each device-related treatment type. Null if that treatment type has never been recorded for the user.

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**catheterChangedAt** | **string** | When the infusion site/cannula was last replaced (SITE_CHANGE) | [optional] [default to undefined]
**reservoirChangedAt** | **string** | When the insulin cartridge was last replaced (INSULIN_CHANGE) | [optional] [default to undefined]
**sensorInsertedAt** | **string** | When the CGM sensor was last inserted (SENSOR_INSERT) | [optional] [default to undefined]

## Example

```typescript
import { DeviceAgeResponse } from './api';

const instance: DeviceAgeResponse = {
    catheterChangedAt,
    reservoirChangedAt,
    sensorInsertedAt,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
