# DefaultApi

All URIs are relative to *http://localhost:8080/api/v1*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**archiveMeasures**](#archivemeasures) | **POST** /users/{userId}/measures/archive | Archive multiple measures|
|[**createMeasure**](#createmeasure) | **POST** /users/{userId}/measures | Create a new measure|
|[**deleteMeasures**](#deletemeasures) | **POST** /users/{userId}/measures/delete | Permanently delete measures|
|[**listMeasures**](#listmeasures) | **GET** /users/{userId}/measures | List all measures for a user|

# **archiveMeasures**
> archiveMeasures(bulkMeasureRequest)

Patients can archive their own measures. Doctors can archive for their allowed patients.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    BulkMeasureRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let bulkMeasureRequest: BulkMeasureRequest; //

const { status, data } = await apiInstance.archiveMeasures(
    userId,
    bulkMeasureRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **bulkMeasureRequest** | **BulkMeasureRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|


### Return type

void (empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Measures archived successfully |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **createMeasure**
> MeasureResponse createMeasure(createMeasureRequest)


### Example

```typescript
import {
    DefaultApi,
    Configuration,
    CreateMeasureRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let createMeasureRequest: CreateMeasureRequest; //

const { status, data } = await apiInstance.createMeasure(
    userId,
    createMeasureRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **createMeasureRequest** | **CreateMeasureRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|


### Return type

**MeasureResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**201** | Measure created successfully |  -  |
|**400** | Bad Request |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **deleteMeasures**
> deleteMeasures(bulkMeasureRequest)

Only doctors and admins can permanently delete measures.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    BulkMeasureRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let bulkMeasureRequest: BulkMeasureRequest; //

const { status, data } = await apiInstance.deleteMeasures(
    userId,
    bulkMeasureRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **bulkMeasureRequest** | **BulkMeasureRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|


### Return type

void (empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Measures deleted successfully |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **listMeasures**
> Array<MeasureResponse> listMeasures()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)

const { status, data } = await apiInstance.listMeasures(
    userId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|


### Return type

**Array<MeasureResponse>**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | List of active measures |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

