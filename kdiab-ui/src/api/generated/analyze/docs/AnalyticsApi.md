# AnalyticsApi

All URIs are relative to *http://localhost:8084/api/v1*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**getDeviceUsageAnalytics**](#getdeviceusageanalytics) | **GET** /users/{userId}/analytics/device-usage | Average device wear durations|

# **getDeviceUsageAnalytics**
> DeviceUsageResult getDeviceUsageAnalytics()

Computes average and standard deviation of device component wear durations from treatment history. Requires at least 2 events of each type to compute a duration.

### Example

```typescript
import {
    AnalyticsApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new AnalyticsApi(configuration);

let userId: string; // (default to undefined)
let days: number; //Number of days to look back (default 90) (optional) (default to 90)

const { status, data } = await apiInstance.getDeviceUsageAnalytics(
    userId,
    days
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **days** | [**number**] | Number of days to look back (default 90) | (optional) defaults to 90|


### Return type

**DeviceUsageResult**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Device usage averages |  -  |
|**401** | JWT missing or invalid |  -  |
|**403** | Authenticated user lacks access to this resource |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

