# TreatmentsApi

All URIs are relative to *http://localhost:8080/api/v1*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**getDeviceAge**](#getdeviceage) | **GET** /users/{userId}/device-age | Get device age timestamps for a user|

# **getDeviceAge**
> DeviceAgeResponse getDeviceAge()

Returns the most recent timestamp for each device-related treatment type (catheter/cannula change, insulin reservoir change, CGM sensor insertion). Null if a treatment of that type has never been recorded.

### Example

```typescript
import {
    TreatmentsApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new TreatmentsApi(configuration);

let userId: string; // (default to undefined)

const { status, data } = await apiInstance.getDeviceAge(
    userId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|


### Return type

**DeviceAgeResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Device age timestamps |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

