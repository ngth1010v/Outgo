data = [1,2,3,4,5,6,7,7]
MIN_LABEL_DISTANCE = 10


# This function will return the real distance on screen of 2 label closet 
def get_distance(step):
    return 0 #placeholder

def get_nice(data: list):
    dmin = min(data)
    dmax = max(data)
    delta = dmax - dmin

    nice_range_min = dmin - delta / 10
    nice_range_max = dmax - delta / 10

    nice_step_base = [1,2,5]
    
    step_base = 0
    if nice_range_min > 0 and nice_range_max > 0:
        step_base = nice_range_min
    elif nice_range_min < 0 and nice_range_max < 0:
        step_base = nice_range_max

    nice_step = 0
    nice_step_base_id = 0
    while get_distance(nice_step) < MIN_LABEL_DISTANCE:
        

    


