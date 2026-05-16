# DeviceStatusData

Pump and uploader device status snapshot (DEVICE_STATUS). Sent periodically by APS clients (AAPS, xDrip+, Juggluco) to report pump reservoir, battery, and client identity.

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**device** | **string** | Name and version of the uploading client application | [default to undefined]
**pumpName** | **string** | Pump model identifier as reported by the client | [optional] [default to undefined]
**reservoirUnits** | **number** | Insulin units remaining in the pump reservoir | [optional] [default to undefined]
**batteryLevel** | **number** | Pump battery level as a percentage (0–100) | [optional] [default to undefined]
**pumpConnected** | **boolean** | Whether the pump was connected at the time of the status report | [optional] [default to undefined]

## Example

```typescript
import { DeviceStatusData } from './api';

const instance: DeviceStatusData = {
    device,
    pumpName,
    reservoirUnits,
    batteryLevel,
    pumpConnected,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
