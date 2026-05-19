# DefaultApi

All URIs are relative to *http://localhost:8084/api/v1*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**getActiveProfiles**](#getactiveprofiles) | **GET** /users/{userId}/profiles/active | Profiles active during a timeframe|
|[**getAgp**](#getagp) | **GET** /users/{userId}/analytics/agp | Ambulatory Glucose Profile — hourly percentiles|
|[**getDeviceAge**](#getdeviceage) | **GET** /users/{userId}/device-age | Get device component ages (proxy to kdiab-treatments)|
|[**getHba1c**](#gethba1c) | **GET** /users/{userId}/analytics/hba1c | HbA1c estimation and time-in-range for a timeframe|
|[**getLatestDeviceStatus**](#getlatestdevicestatus) | **GET** /users/{userId}/device-status | Get latest pump/CGM device status (proxy to kdiab-treatments)|
|[**getTimeline**](#gettimeline) | **GET** /users/{userId}/timeline | Combined measures and treatments for a timeframe|

# **getActiveProfiles**
> ProfilesResponse getActiveProfiles()

Returns the insulin pump profiles that were active or archived during the given time window, including segment data (basal, ICR, ISF, targets).

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let from: string; //Start of timeframe (ISO-8601 datetime) (default to undefined)
let to: string; //End of timeframe (ISO-8601 datetime) (default to undefined)

const { status, data } = await apiInstance.getActiveProfiles(
    userId,
    from,
    to
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **from** | [**string**] | Start of timeframe (ISO-8601 datetime) | defaults to undefined|
| **to** | [**string**] | End of timeframe (ISO-8601 datetime) | defaults to undefined|


### Return type

**ProfilesResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Active profiles during the given timeframe |  -  |
|**401** | JWT missing or invalid |  -  |
|**403** | Authenticated user lacks access to this resource |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getAgp**
> AgpResponse getAgp()

Groups CGM readings by UTC hour (0–23) and returns p10/p25/p50/p75/p90 percentiles for each bucket. Buckets with no readings have null percentile values.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let from: string; //Start of timeframe (ISO-8601 datetime) (default to undefined)
let to: string; //End of timeframe (ISO-8601 datetime) (default to undefined)

const { status, data } = await apiInstance.getAgp(
    userId,
    from,
    to
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **from** | [**string**] | Start of timeframe (ISO-8601 datetime) | defaults to undefined|
| **to** | [**string**] | End of timeframe (ISO-8601 datetime) | defaults to undefined|


### Return type

**AgpResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | AGP hourly data (24 buckets) |  -  |
|**401** | JWT missing or invalid |  -  |
|**403** | Authenticated user lacks access to this resource |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getDeviceAge**
> DeviceAgeResponse getDeviceAge()

Returns the most recent timestamp for each device-related treatment type (catheter, reservoir, CGM sensor). Proxied from kdiab-treatments.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

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
|**200** | Device component timestamps |  -  |
|**401** | JWT missing or invalid |  -  |
|**403** | Authenticated user lacks access to this resource |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getHba1c**
> Hba1cResponse getHba1c()

Computes an estimated HbA1c (DCCT formula) and time-in-range breakdown from CGM readings in the given window. Returns null hba1c if no CGM data is available.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let from: string; //Start of timeframe (ISO-8601 datetime) (default to undefined)
let to: string; //End of timeframe (ISO-8601 datetime) (default to undefined)

const { status, data } = await apiInstance.getHba1c(
    userId,
    from,
    to
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **from** | [**string**] | Start of timeframe (ISO-8601 datetime) | defaults to undefined|
| **to** | [**string**] | End of timeframe (ISO-8601 datetime) | defaults to undefined|


### Return type

**Hba1cResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | HbA1c analytics result |  -  |
|**401** | JWT missing or invalid |  -  |
|**403** | Authenticated user lacks access to this resource |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getLatestDeviceStatus**
> DeviceStatusResponse getLatestDeviceStatus()

Returns the most recently recorded pump and uploader device status snapshot. Returns 204 if no status has been recorded yet. Proxied from kdiab-treatments.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

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
|**204** | No device status found |  -  |
|**401** | JWT missing or invalid |  -  |
|**403** | Authenticated user lacks access to this resource |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getTimeline**
> TimelineResponse getTimeline()

Returns all CGM/BGM measures and treatment events for the user within the given time window, merged into a single chronological list.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let from: string; //Start of timeframe (ISO-8601 datetime) (default to undefined)
let to: string; //End of timeframe (ISO-8601 datetime) (default to undefined)

const { status, data } = await apiInstance.getTimeline(
    userId,
    from,
    to
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **from** | [**string**] | Start of timeframe (ISO-8601 datetime) | defaults to undefined|
| **to** | [**string**] | End of timeframe (ISO-8601 datetime) | defaults to undefined|


### Return type

**TimelineResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Combined timeline data |  -  |
|**401** | JWT missing or invalid |  -  |
|**403** | Authenticated user lacks access to this resource |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

