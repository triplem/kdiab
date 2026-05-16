# DeviceStatusResponse

Most recent pump and uploader device status snapshot for a user

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **string** | Unique identifier of the device status record | [default to undefined]
**userId** | **string** | User the status belongs to | [default to undefined]
**recordedAt** | **string** | When the status was recorded by the APS client | [default to undefined]
**device** | **string** | Name and version of the uploading client application | [default to undefined]
**pumpName** | **string** | Pump model identifier as reported by the client | [optional] [default to undefined]
**reservoirUnits** | **number** | Insulin units remaining in the pump reservoir | [optional] [default to undefined]
**batteryLevel** | **number** | Pump battery level as a percentage (0–100) | [optional] [default to undefined]
**pumpConnected** | **boolean** | Whether the pump was connected at the time of the status report | [optional] [default to undefined]

## Example

```typescript
import { DeviceStatusResponse } from './api';

const instance: DeviceStatusResponse = {
    id,
    userId,
    recordedAt,
    device,
    pumpName,
    reservoirUnits,
    batteryLevel,
    pumpConnected,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
