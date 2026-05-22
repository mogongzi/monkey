#include "object.h"

bool mobject_is_truthy(MObject *obj) {
  switch (obj->type) {
  case MBOOLEAN:
    return obj->as.boolean;
  case MNULL:
    return false;
  case MINTEGER:
    return true;
  default:
    return true;
  }
}
