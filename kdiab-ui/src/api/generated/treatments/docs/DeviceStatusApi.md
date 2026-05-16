# DeviceStatusApi

All URIs are relative to *http://localhost:8080/api/v1*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**getLatestDeviceStatus**](#getlatestdevicestatus) | **GET** /users/{userId}/device-status/latest | Get the latest device status for a user|

# **getLatestDeviceStatus**
> DeviceStatusResponse getLatestDeviceStatus()

Returns the most recently recorded pump and uploader device status snapshot for the given user. Returns 404 if no status has been recorded yet.

### Example

```typescript
import {
    DeviceStatusApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DeviceStatusApi(configuration);

let userId: string; // (default to undefined)

const { status, data } = await apiInstance.getLatestDeviceStatus(
    userId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|


### Return type

**DeviceStatusResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Latest device status |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |
|**404** | Not Found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

